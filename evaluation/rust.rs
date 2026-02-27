fn main() {
    println!("Hello, world!");
}

#[allow(unused)]
fn use_immut<T>(o: &T) {}

#[allow(unused)]
fn use_mut<T>(o: &mut T) {}

fn basic() {
    let x = &mut Box::new(0);
    let y: &_ = x;
    let z: &mut _ = &mut Box::new(1);
    use_mut(z);
    let z = y;
    use_immut(z);
    use_immut(x);
}

struct A {
    x: Box<usize>,
    y: Box<usize>,
}

impl A {
    fn get_x(&mut self) -> &mut Box<usize> {
        &mut self.x
    }

    fn get_y(&self) -> &Box<usize> {
        &self.y
    }

    fn set_x(&mut self, v: Box<usize>) {
        self.x = v;
    }
}

fn field_access(a: &mut A) {
    let x = &mut a.x;
    let y = &a.y;
    use_immut(x);
    use_immut(y);
    use_immut(a);
}

struct C {
    a1: A,
    a2: A
}

fn field_assignment(c: &mut C) {
    let c2 = &mut *c;
    c2.a1.x = Box::new(2);
    let a = &mut c.a2;
    a.x = Box::new(3);
    use_mut(c);
}

/*fn parallel_getters(a: &mut A) {
    let x = a.get_x();
    let y = a.get_y();
    use_mut(x);
    use_immut(y);
}*/

struct B {
    a: A,
}

fn nested(b: &B) {
    let x = &b.a.x; // Could also be mutable
    let y = &b.a.y;
    use_immut(x);
    use_immut(y);
}

fn setters(a: &mut A) {
    let new_x = Box::new(2);
    a.set_x(new_x);
    use_mut(a);
}

fn arrays() {
    let mut arr = [Box::new(0), Box::new(1)];
    // Arrays have to be mut to get a mut reference to an element
    // This makes sense in rust, as the mut reference can also
    // reassign the array element
    let x = &mut arr[0];
    use_mut(x);
}

fn arrays2(a1: A, a2: A, i: usize, j: usize) {
    let mut arr = [a1, a2];
    let x = &mut arr[i].x;
    let y = &arr[j].y;

    use_mut(x);
    use_immut(y);
}
